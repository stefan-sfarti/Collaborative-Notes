package com.collabnotes.collabnotes.repository;

import com.collabnotes.collabnotes.entity.Collaborator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaboratorRepository extends JpaRepository<Collaborator, Long> {
    List<Collaborator> findByNoteId(String noteId);
    
    Optional<Collaborator> findByNoteIdAndUserId(String noteId, String userId);
    
    boolean existsByNoteIdAndUserId(String noteId, String userId);
    
    void deleteByNoteIdAndUserId(String noteId, String userId);
}
