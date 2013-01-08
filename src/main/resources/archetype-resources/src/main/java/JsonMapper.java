#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
/*
 * Copyright 2013 Strumsoft Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ${package};

import static org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.codehaus.jackson.map.SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.INDENT_OUTPUT;
import static org.codehaus.jackson.map.SerializationConfig.Feature.SORT_PROPERTIES_ALPHABETICALLY;
import static org.codehaus.jackson.map.SerializationConfig.Feature.USE_ANNOTATIONS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS;

import org.codehaus.jackson.map.ObjectMapper;

public class JsonMapper {

    private static final ObjectMapper jackson;
    static {
        jackson = new ObjectMapper();
        jackson.configure(USE_ANNOTATIONS, true);
        jackson.configure(INDENT_OUTPUT, true);
        jackson.configure(WRITE_DATES_AS_TIMESTAMPS, true);
        jackson.configure(SORT_PROPERTIES_ALPHABETICALLY, true);
        jackson.configure(FAIL_ON_EMPTY_BEANS, false);
        jackson.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper getMapper() {
        return jackson;
    }
}
