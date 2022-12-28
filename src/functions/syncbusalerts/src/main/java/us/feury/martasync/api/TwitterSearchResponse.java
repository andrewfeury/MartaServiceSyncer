package us.feury.martasync.api;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TwitterSearchResponse {
    private List<TwitterSearchData> data = new ArrayList<>();
    private TwitterSearchMetadata meta;

    public List<TwitterSearchData> getData() {
        return data;
    }

    public void setData(List<TwitterSearchData> data) {
        this.data = data;
    }

    public TwitterSearchMetadata getMeta() {
        return meta;
    }

    public void setMeta(TwitterSearchMetadata meta) {
        this.meta = meta;
    }

    @Override
    public String toString() {
        return String.format("TwitterSearchResponse [data=%s, meta=%s]", data, meta);
    }
}
