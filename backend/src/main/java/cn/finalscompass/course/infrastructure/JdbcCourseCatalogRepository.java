package cn.finalscompass.course.infrastructure;

import cn.finalscompass.course.domain.CourseCatalogRepository;
import cn.finalscompass.model.ApiModels.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** 课程导航持久化的 JDBC 适配器 */
@Repository
public class JdbcCourseCatalogRepository implements CourseCatalogRepository {
  private final JdbcClient jdbc;
  public JdbcCourseCatalogRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

  @Override public List<Course> findCourses() {
    return jdbc.sql("""
        SELECT c.id,c.slug,c.name,c.code,c.category,cp.college,cp.program_name,cp.course_type
        FROM course c JOIN course_program cp ON cp.course_id=c.id WHERE c.active=TRUE
        ORDER BY cp.college,cp.program_name,cp.course_type,c.name
        """).query(Course.class).list();
  }
  @Override public List<College> findColleges() {
    return jdbc.sql("SELECT id,name FROM college WHERE active=TRUE ORDER BY created_at,name")
        .query(College.class).list();
  }
  @Override public College createCollege(CreateCollege input) {
    String name=input.name().trim();
    if(jdbc.sql("SELECT COUNT(*) FROM college WHERE name=:name").param("name",name)
        .query(Integer.class).single()>0) throw conflict("学院已存在");
    jdbc.sql("INSERT INTO college(name) VALUES (:name)").param("name",name).update();
    long id=jdbc.sql("SELECT id FROM college WHERE name=:name").param("name",name).query(Long.class).single();
    return new College(id,name);
  }
  @Override public List<Program> findPrograms() {
    return jdbc.sql("""
        SELECT p.id,p.name,c.name college FROM academic_program p JOIN college c ON c.id=p.college_id
        WHERE p.active=TRUE AND c.active=TRUE ORDER BY c.created_at,p.created_at,p.name
        """).query(Program.class).list();
  }
  @Override public Program createProgram(CreateProgram input) {
    String college=input.college().trim(),name=input.name().trim();
    long collegeId=jdbc.sql("SELECT id FROM college WHERE name=:name AND active=TRUE")
        .param("name",college).query(Long.class).optional()
        .orElseThrow(()->badRequest("请先添加所属学院"));
    if(jdbc.sql("SELECT COUNT(*) FROM academic_program WHERE college_id=:college AND name=:name")
        .param("college",collegeId).param("name",name).query(Integer.class).single()>0)
      throw conflict("该学院下已存在同名专业");
    jdbc.sql("INSERT INTO academic_program(college_id,name) VALUES (:college,:name)")
        .param("college",collegeId).param("name",name).update();
    long id=jdbc.sql("SELECT id FROM academic_program WHERE college_id=:college AND name=:name")
        .param("college",collegeId).param("name",name).query(Long.class).single();
    return new Program(id,name,college);
  }
  @Override public Course createCourse(CreateCourse input) {
    String name=input.name().trim(),code=input.code().trim().toUpperCase(),category=input.category().trim();
    String college=input.college().trim();
    String program=input.programName()==null?null:input.programName().trim();
    String type=input.courseType()==null?null:input.courseType().trim();
    if(jdbc.sql("SELECT COUNT(*) FROM college WHERE name=:name AND active=TRUE").param("name",college)
        .query(Integer.class).single()==0) throw badRequest("请先添加所属学院");
    CourseIdentity existing=jdbc.sql("""
        SELECT id,slug,name,category FROM course WHERE code=:code AND name=:name AND active=TRUE
        ORDER BY id LIMIT 1
        """).param("code",code).param("name",name).query(CourseIdentity.class).optional().orElse(null);
    if(existing==null){
      CourseIdentity other=jdbc.sql("""
          SELECT id,slug,name,category FROM course WHERE code=:code AND active=TRUE ORDER BY id LIMIT 1
          """).param("code",code).query(CourseIdentity.class).optional().orElse(null);
      if(other!=null) throw conflict("课程代码 "+code+" 已属于“"+other.name()+"”，请检查课程名称");
    }
    if(existing!=null){
      link(existing.id(),college,program,type);
      return new Course(existing.id(),existing.slug(),existing.name(),code,existing.category(),college,program,type);
    }
    String slug="course-"+UUID.randomUUID().toString().substring(0,8);
    jdbc.sql("INSERT INTO course(slug,name,code,category,college,program_name,course_type) VALUES (:slug,:name,:code,:category,:college,:program,:type)")
        .param("slug",slug).param("name",name).param("code",code).param("category",category)
        .param("college",college).param("program",program).param("type",type).update();
    long id=jdbc.sql("SELECT id FROM course WHERE slug=:slug").param("slug",slug).query(Long.class).single();
    link(id,college,program,type);
    return new Course(id,slug,name,code,category,college,program,type);
  }
  private void link(long id,String college,String program,String type){
    jdbc.sql("""
        INSERT INTO course_program(course_id,college,program_name,course_type)
        VALUES (:course,:college,:program,:type) ON DUPLICATE KEY UPDATE course_type=VALUES(course_type)
        """).param("course",id).param("college",college).param("program",program).param("type",type).update();
  }
  @Override public List<Teacher> findTeachers(String slug) {
    return jdbc.sql("""
        SELECT t.id,t.slug,t.name,t.college,COUNT(DISTINCT r.id) resource_count,COUNT(DISTINCT d.id) post_count
        FROM teacher t JOIN teacher_course tc ON tc.teacher_id=t.id JOIN course c ON c.id=tc.course_id
        LEFT JOIN resource r ON r.teacher_id=t.id AND r.course_id=c.id AND r.status='PUBLISHED'
        LEFT JOIN discussion d ON d.teacher_id=t.id AND d.course_id=c.id AND d.status='VISIBLE'
        WHERE c.slug=:slug GROUP BY t.id,t.slug,t.name,t.college ORDER BY t.name
        """).param("slug",slug).query(Teacher.class).list();
  }
  @Override public Teacher createTeacher(String courseSlug,CreateTeacher input) {
    long courseId=jdbc.sql("SELECT id FROM course WHERE slug=:slug AND active=TRUE").param("slug",courseSlug)
        .query(Long.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"课程不存在"));
    String name=input.name().trim(),college=input.college().trim();
    TeacherIdentity teacher=jdbc.sql("SELECT id,slug FROM teacher WHERE name=:name AND college=:college LIMIT 1")
        .param("name",name).param("college",college).query(TeacherIdentity.class).optional().orElse(null);
    if(teacher==null){
      String slug="teacher-"+UUID.randomUUID().toString().substring(0,8);
      jdbc.sql("INSERT INTO teacher(slug,name,college) VALUES (:slug,:name,:college)")
          .param("slug",slug).param("name",name).param("college",college).update();
      teacher=new TeacherIdentity(jdbc.sql("SELECT id FROM teacher WHERE slug=:slug").param("slug",slug)
          .query(Long.class).single(),slug);
    }
    jdbc.sql("INSERT IGNORE INTO teacher_course(teacher_id,course_id,term,review_note) VALUES (:teacher,:course,'当前学期','同学共建页面，具体考试范围以课堂通知为准。')")
        .param("teacher",teacher.id()).param("course",courseId).update();
    return new Teacher(teacher.id(),teacher.slug(),name,college,0,0);
  }
  private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
  private ResponseStatusException badRequest(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  private record CourseIdentity(long id,String slug,String name,String category){}
  private record TeacherIdentity(long id,String slug){}
}
