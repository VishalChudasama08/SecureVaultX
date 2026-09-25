package com.securevaultx.backend.config;

import com.securevaultx.backend.storage.FileStorage;
import com.securevaultx.backend.storage.LocalFileStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    FileStorage fileStorage(StorageProperties props) {
        return new LocalFileStorage(props.root());
    }
}
