package com.saraiev.ebayscraper.repository;

import com.saraiev.ebayscraper.domain.Ad;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AdRepository extends CrudRepository<Ad, Long> {

    List<Ad> findAll();

    Ad findByUrl(String url);

}
