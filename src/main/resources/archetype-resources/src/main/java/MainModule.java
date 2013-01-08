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

import java.io.FileInputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import ${package}.resource.ResourcesModule;

public class MainModule extends AbstractModule {
    // log
    private static final Logger log = LoggerFactory.getLogger(MainModule.class);
    // Conf 
    static final String CONF_FILE = "app.properties";
    static final String CONF_PARAM = "app.configurationFile";

    @Override
    protected void configure() {
        // Read properties
        try {
            Properties props = loadProperties();
            log.info("==>> App Properties = {}", props);
            Names.bindProperties(binder(), props);
        } catch (Exception e) {
            log.error("error in configure ==> {}", e);
        }
        
        // Install other modules
        install(new ResourcesModule());
    }

    private static Properties loadProperties() throws Exception {
        Properties properties = new Properties();

        // Load from class-path
        ClassLoader loader = MainModule.class.getClassLoader();
        URL url = loader.getResource(CONF_FILE);
        properties.load(url.openStream());

        // from file
        String propFile = System.getProperty(CONF_PARAM);
        if (null != propFile) {
            properties.load(new FileInputStream(propFile));
        }

        // Override explicitly provided properties
        for (Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            properties.put(key, System.getProperty(key, (String) entry.getValue()));
        }
        return properties;
    }
}
