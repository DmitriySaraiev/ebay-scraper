package com.saraiev.ebayscraper.repository;

import com.saraiev.ebayscraper.domain.Ad;
import org.springframework.data.repository.CrudRepository;

public interface AdRepository extends CrudRepository<Ad, Long> {
}
