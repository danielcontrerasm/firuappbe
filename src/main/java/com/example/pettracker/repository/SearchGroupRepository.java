package com.example.pettracker.repository;

import com.example.pettracker.model.SearchGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchGroupRepository extends JpaRepository<SearchGroup, Long> {

    List<SearchGroup> findByPetIdOrderByCreatedAtDesc(Long petId);
}
