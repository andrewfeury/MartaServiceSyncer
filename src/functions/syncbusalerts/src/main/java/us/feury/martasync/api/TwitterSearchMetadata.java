package us.feury.martasync.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TwitterSearchMetadata {

    @JsonProperty("newest_id")
    private String newestTweetId;

    @JsonProperty("oldest_id")
    private String oldestTweetId;

    @JsonProperty("result_count")
    private Integer resultCount;

    public String getNewestTweetId() {
        return newestTweetId;
    }

    public void setNewestTweetId(String newestTweetId) {
        this.newestTweetId = newestTweetId;
    }

    public String getOldestTweetId() {
        return oldestTweetId;
    }

    public void setOldestTweetId(String oldestTweetId) {
        this.oldestTweetId = oldestTweetId;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }

    @Override
    public String toString() {
        return String.format("TwitterSearchMetadata [newestTweetId=%s, oldestTweetId=%s, resultCount=%s]",
                newestTweetId, oldestTweetId, resultCount);
    }
}
