package com.saraiev.ebayscraper.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class DownloaderConfig {

    private static final int TIMEOUT_SECONDS = 60;


    private OkHttpClient createOkHttpClient() {
        WebDriverManager.chromedriver().setup();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return builder.build();
    }

    @Bean
    public OkHttpClient okHttpClient() {
        return createOkHttpClient();
    }

}
