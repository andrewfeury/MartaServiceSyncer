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

package us.feury.martasync;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class MartaQueryOutput {
    
    private Map<String, MartaServiceTweet> tweetsByRoute = new LinkedHashMap<>();

    public Map<String, MartaServiceTweet> getTweetsByRoute() {
        return tweetsByRoute;
    }

    public MartaServiceTweet putTweet(String route, String createdAt, String text) {
        MartaServiceTweet tweet = new MartaServiceTweet(createdAt, text);
        return tweetsByRoute.merge(route, tweet, this::mergeTweets);
    }

    private MartaServiceTweet mergeTweets(MartaServiceTweet existing, MartaServiceTweet update) {
        
        if (ZonedDateTime.parse(update.getLastUpdated())
                .isAfter(ZonedDateTime.parse(existing.getLastUpdated()))) {
            // New tweet is more recent
            String newText = String.join("\n\n", update.getText(), existing.getText());
            return new MartaServiceTweet(update.getLastUpdated(), newText);
        } else {
            // Existing tweet is more recent
            String newText = String.join("\n\n", existing.getText(), update.getText());
            return new MartaServiceTweet(existing.getLastUpdated(), newText);
        }
    }

    @Override
    public String toString() {
        return String.format("MartaQueryOutput [tweetsByRoute=%s]", tweetsByRoute);
    }
}
