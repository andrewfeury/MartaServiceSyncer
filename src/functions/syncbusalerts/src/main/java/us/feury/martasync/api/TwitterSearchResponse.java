/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
