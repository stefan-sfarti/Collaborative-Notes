package com.collabnotes.collabnotes.repository;

import com.collabnotes.collabnotes.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, String> {
    List<Note> findByOwnerId(String ownerId);

    @Query("SELECT n FROM Note n JOIN n.collaborators c WHERE c.user.id = :userId")
    List<Note> findByCollaboratorUserId(@Param("userId") String userId);
}
