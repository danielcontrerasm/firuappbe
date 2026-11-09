package com.example.pettracker.repository;

import com.example.pettracker.entity.SearchGroupMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchGroupMessageRepository extends JpaRepository<SearchGroupMessage, Long> {

    @EntityGraph(attributePaths = {"searchGroup", "sender"})
    List<SearchGroupMessage> findBySearchGroupIdOrderByCreatedAtAsc(Long searchGroupId, Pageable pageable);
}
