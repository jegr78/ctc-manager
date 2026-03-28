package de.ctc.domain.repository;

import de.ctc.domain.model.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TrackRepository extends JpaRepository<Track, UUID> {
    List<Track> findAllByOrderByNameAsc();
    boolean existsByName(String name);
}
