/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2014 creation

*/
package com.github.lindenb.jvarkit.tools.biostar;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;

import com.github.lindenb.jvarkit.util.picard.SAMSequenceDictionaryProgress;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;

public class Biostar78285 extends AbstractBiostar78285
	{
	private static final org.slf4j.Logger LOG = com.github.lindenb.jvarkit.util.log.Logging.getLog(Biostar78285.class);

    private  Collection<Throwable> scan(final SamReader samFileReader,final PrintStream out) throws IOException
    	{
    	SAMRecordIterator iter=null;
    	try
	    	{
	    	
	    	SAMFileHeader header=samFileReader.getFileHeader();
	    	if(header.getSortOrder()!=SortOrder.coordinate)
	    		{
	    		return wrapException("Sam file is not sorted on coordinate :"+header.getSortOrder());
	    		}
	    	SAMSequenceDictionary dict=header.getSequenceDictionary();
	    	if(dict==null)
	    		{
	    		return wrapException("SamFile doesn't contain a SAMSequenceDictionary.");
	    		}
	    	/* flag, do we saw all chromosomes in dictionary ? */
	    	boolean seen_tid[]=new boolean[dict.getSequences().size()];
	    	Arrays.fill(seen_tid, false);
	    	
	    	
    		BitSet mapped=null;
    		SAMSequenceRecord ssr=null;
    		SAMSequenceDictionaryProgress progress=new SAMSequenceDictionaryProgress(dict);
    		iter=samFileReader.iterator();
	    	while(iter.hasNext())
	    		{
	    		SAMRecord rec=progress.watch(iter.next());
	    		if(rec.getReadUnmappedFlag()) continue;
	    		if(rec.isSecondaryOrSupplementary()) continue;
	    		if(rec.getDuplicateReadFlag()) continue;
	    		if(rec.getReadFailsVendorQualityCheckFlag()) continue;
	    		if(rec.getMappingQuality()==0) continue;
	    		
	    		Cigar cigar=rec.getCigar();
	    		if(cigar==null) continue;
	    		if(ssr==null || ssr.getSequenceIndex()!=rec.getReferenceIndex())
	    			{
	    			if(ssr!=null && mapped!=null)
	    				{
		    			dump(ssr,mapped,out);
		    			}
	    			ssr=dict.getSequence(rec.getReferenceIndex());
	    			if(ssr==null)
	    				{
	    				return wrapException("Sequence not in dict :"+rec);
	    				}
	    			LOG.info("allocating bitset for "+ssr.getSequenceName()+" LENGTH="+ssr.getSequenceLength());
	    			mapped=new BitSet(ssr.getSequenceLength());
	    			seen_tid[rec.getReferenceIndex()]=true;
	    			}
	    		int refpos0=rec.getAlignmentStart()-1;
	    		for(CigarElement ce:cigar.getCigarElements())
	    			{
	    			CigarOperator op=ce.getOperator();
	    			if(op.consumesReferenceBases())
	    				{	
	    				if(op.consumesReadBases())
	    					{
	    					for(int i=0;i< ce.getLength() && refpos0 +i < ssr.getSequenceLength();++i)
	    		    			{
	    						mapped.set(refpos0+i,true);
    		    				}
	    					}
	    				refpos0 += ce.getLength();
	    				}		    				
	    			}
	    		}
	    	
	    	
	    	if(ssr!=null && mapped!=null)
				{
    			dump(ssr,mapped,out);
    			}
	    		
				
				
	    	/* unseen chromosomes */
	    	for(int i=0;i< seen_tid.length;++i)
	    		{
	    		if(seen_tid[i]) continue;
	    		ssr=dict.getSequence(i);
    			System.out.println(ssr.getSequenceName()+"\t0\t"+ssr.getSequenceLength());
	    		}
	    	out.flush();
	    	progress.finish();
	    	return RETURN_OK;
	    	}
    	catch(Exception err)
    		{
    		return wrapException(err);
    		}
    	finally
    		{
    		CloserUtil.close(iter);
    		CloserUtil.close(samFileReader);
    		}
    	
    	}
    private void dump(SAMSequenceRecord ssr,BitSet mapped,final PrintStream out)
    	{
    	int i=0;
    	while(i<ssr.getSequenceLength())
    		{
    		if(mapped.get(i))
    			{
    			++i;
    			continue;
    			}
    		int j=i+1;
    		while(j<ssr.getSequenceLength() && !mapped.get(j))
    			{
    			++j;
        		}
    		out.println(ssr.getSequenceName()+"\t"+i+"\t"+j);
    		i=j;
    		}
    	}
    
    @Override
    protected Collection<Throwable> call(String inputName) throws Exception {
    	SamReader samReader=null;
    	PrintStream out=null;
		try
			{
			out = openFileOrStdoutAsPrintStream();
			samReader = openSamReader(inputName); 
			return scan(samReader,out);
			}
		catch(Exception err)
			{
			return wrapException(err);
			}
		finally
			{
			CloserUtil.close(out);
			CloserUtil.close(samReader);
			}
		}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new Biostar78285().instanceMainWithExit(args);

	}

}
