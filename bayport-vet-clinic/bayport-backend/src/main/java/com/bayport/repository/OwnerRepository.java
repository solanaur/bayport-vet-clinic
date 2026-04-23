package com.bayport.repository;

import com.bayport.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    @Query("""
        SELECT o FROM Owner o
        WHERE LOWER(o.fullName) LIKE LOWER(:pattern)
           OR LOWER(o.phone) LIKE LOWER(:pattern)
           OR LOWER(o.email) LIKE LOWER(:pattern)
        ORDER BY o.fullName
        """)
    List<Owner> search(@Param("pattern") String pattern);
}

