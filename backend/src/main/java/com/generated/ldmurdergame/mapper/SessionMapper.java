package com.generated.ldmurdergame.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import com.generated.ldmurdergame.model.GameSession;
import com.generated.ldmurdergame.model.SessionView;

@Mapper
public interface SessionMapper {
  @Insert("INSERT INTO game_sessions (script_id, start_time, host_name, capacity) "
      + "VALUES (#{scriptId}, #{startTime}, #{hostName}, #{capacity})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(GameSession session);

  @Select("SELECT * FROM game_sessions WHERE id = #{id}")
  GameSession findById(@Param("id") Long id);

  /** 报名/取消时对场次行加排他锁，保证“检查名额 + 写入报名”在并发下仍然正确。 */
  @Select("SELECT * FROM game_sessions WHERE id = #{id} FOR UPDATE")
  GameSession findByIdForUpdate(@Param("id") Long id);

  @Select("SELECT gs.id, gs.script_id AS script_id, gs.start_time AS start_time, gs.host_name AS host_name, "
      + "gs.capacity AS capacity, gs.created_at AS created_at, s.name AS script_name, s.genre AS genre, "
      + "s.difficulty AS difficulty, "
      + "(SELECT COUNT(*) FROM session_registrations r WHERE r.session_id = gs.id) AS registered_count "
      + "FROM game_sessions gs JOIN scripts s ON s.id = gs.script_id "
      + "ORDER BY gs.start_time ASC, gs.id ASC")
  List<SessionView> findAllViews();

  @Select("SELECT gs.id, gs.script_id AS script_id, gs.start_time AS start_time, gs.host_name AS host_name, "
      + "gs.capacity AS capacity, gs.created_at AS created_at, s.name AS script_name, s.genre AS genre, "
      + "s.difficulty AS difficulty, "
      + "(SELECT COUNT(*) FROM session_registrations r WHERE r.session_id = gs.id) AS registered_count "
      + "FROM game_sessions gs JOIN scripts s ON s.id = gs.script_id WHERE gs.id = #{id}")
  SessionView findViewById(@Param("id") Long id);

  @Select("<script>"
      + "SELECT * FROM session_registrations WHERE session_id IN "
      + "<foreach collection='ids' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
      + "ORDER BY created_at ASC"
      + "</script>")
  List<com.generated.ldmurdergame.model.SessionRegistration> findBySessionIds(@Param("ids") List<Long> ids);

  @Select("SELECT * FROM game_sessions WHERE script_id = #{scriptId} AND start_time > #{now}")
  List<GameSession> findUpcomingByScript(@Param("scriptId") Long scriptId, @Param("now") LocalDateTime now);
}
