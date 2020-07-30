package com.saraiev.ebayscraper.repository;

import com.saraiev.ebayscraper.domain.Category;
import org.springframework.data.repository.CrudRepository;

public interface CategoryRepository extends CrudRepository<Category, Long> {
}
