package com.example.pettracker.repository;

import com.example.pettracker.model.SearchVolunteer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchVolunteerRepository extends JpaRepository<SearchVolunteer, Long> {

    List<SearchVolunteer> findBySearchGroupId(Long searchGroupId);

    Optional<SearchVolunteer> findBySearchGroupIdAndUserId(Long searchGroupId, Long userId);
}
