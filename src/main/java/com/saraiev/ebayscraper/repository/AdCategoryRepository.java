package com.saraiev.ebayscraper.repository;

import com.saraiev.ebayscraper.domain.AdCategory;
import org.springframework.data.repository.CrudRepository;

public interface AdCategoryRepository extends CrudRepository<AdCategory, Long> {
}
