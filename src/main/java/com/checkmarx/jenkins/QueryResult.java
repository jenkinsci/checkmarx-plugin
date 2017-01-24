package com.checkmarx.jenkins;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by zoharby on 22/01/2017.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryResult {

        @Nullable
        @JsonProperty("name")
        private String name;
        @Nullable
        @JsonProperty("severity")
        private String severity;
        @JsonProperty("count")
        private int count;

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(@Nullable String name) {
            this.name = name;
        }

        @Nullable
        public String getSeverity() {
            return severity;
        }

        public void setSeverity(@Nullable String severity) {
            this.severity = severity;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @NotNull
        public String getPrettyName() {
            if (this.name != null) {
                return this.name.replace('_', ' ');
            } else {
                return "";
            }
        }
}
