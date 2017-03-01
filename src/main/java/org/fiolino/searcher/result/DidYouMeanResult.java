package org.fiolino.searcher.result;

public class DidYouMeanResult implements Comparable<DidYouMeanResult> {

	private final String queryString;

	private final long hits;

	public DidYouMeanResult(String queryString, long hits) {
		this.queryString = queryString;
		this.hits = hits;
	}

	@Override
	public boolean equals(Object obj) {
		try {
			return queryString.equals(((DidYouMeanResult) obj).queryString);
		} catch(Exception e) {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return queryString.hashCode();
	}

	@Override
	public int compareTo(DidYouMeanResult o) {
		if(hits > o.hits) {
			return -1;
		} else if(hits < o.hits) {
			return +1;
		}
		return 0;
	}

	public String getQueryString() {
		return queryString;
	}

	public long getHits() {
		return hits;
	}
}
