package com.saraiev.ebayscraper.tools;

import okhttp3.*;
import org.openqa.selenium.Cookie;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


@Service
public class Downloader {

    private OkHttpClient client;

    public Downloader(OkHttpClient client) {
        this.client = client;
    }

    public String get(String url, Set<Cookie> cookies) throws IOException {
        Request request = buildRequest(url, cookies);
        Response response = getResponse(request);
        return response.body().string();
    }

    public String get(String url) throws IOException {
        Request request = buildRequest(url);
        Response response = getResponse(request);
        return response.body().string();
    }

    public String post(String url, Map<String, String> params) throws IOException {
        Response response = postResponse(url, params);
        return response.body().string();
    }

    public String post(String url, String body) throws IOException {
        Response response = postResponse(url, body);
        return response.body().string();
    }

    private Response postResponse(String url, String body) throws IOException {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(JSON, body);
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "*application/json")
                .header("cookie", "yandexuid=5066137771578931999; yuidss=5066137771578931999; ymex=1584991431.oyu.5196297621582397991#1897757996.yrts.1582397996#1897759431.yrtsi.1582399431; gdpr=0; _ym_d=1583458250; _ym_uid=1583458250442838161; mda=0; Session_id=3:1583458262.5.0.1583458262324:ko7jFw:cf.1|104552666.0.2|213539.198873.hXB2M6H9nZoTqka6ZA9DeOLlWXA; sessionid2=3:1583458262.5.0.1583458262324:ko7jFw:cf.1|104552666.0.2|213539.345708.wyFvTG5_S9gWK4kxRIeHYJ_dh9U; yp=1582485831.yu.5196297621582397991#1898818262.udn.cDpEaW1ldHJpeTE5OTY%3D; L=cXJiBlNWY31xY0V6bn1hUHwBAApsdltEBjMPJiQkLz95cXtY.1583458262.14162.342462.efd11bd8e47c2a59ff33f2ac6726fabe; yandex_login=Dimetriy1996; i=DAWCVyjYuHEhO6113liblaccg8CjW9sB3lxQtRrpF0Phv1XuOoWy1xuDYvQw41k6KUdNnJMqqXBIqCo14TdgoRrYOM8=; EMCzniGaQ=1; _ym_isad=1; _ym_visorc_49540177=b; ys=ymrefl.EAE2C3DD01B2F04E")
                .post(requestBody)
                .build();
        return client.newCall(request).execute();
    }

    private Response postResponse(String url, Map<String, String> params) throws IOException {
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry entry : params.entrySet()) {
            builder.add(entry.getKey().toString(), entry.getValue().toString());
        }
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "*application/json")
                .post(builder.build())
                .build();
        return client.newCall(request).execute();
    }

    private Response getResponse(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    private Request buildRequest(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36");
//                .header("Accept-Encoding", "*")
        return builder.build();
    }

    private Request buildRequest(String url, Set<Cookie> cookies) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .header("Accept-Encoding", "*")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36");
        Iterator<Cookie> iterator = cookies.iterator();
        while (iterator.hasNext()) {
            Cookie cookie = (Cookie) iterator.next();
            builder = builder.addHeader("Cookie", cookie.getName() + "=" + cookie.getValue());
        }
        return builder.build();
    }

}
