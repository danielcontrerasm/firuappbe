package com.example.pettracker.repository;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchGroupRepository extends JpaRepository<SearchGroup, Long> {

    List<SearchGroup> findByPet(Pet pet);

    List<SearchGroup> findByPetId(Long petId);
}
