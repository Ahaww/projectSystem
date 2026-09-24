package edu.wylie.crs.config;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Enrollment;
import edu.wylie.crs.entity.Professor;
import edu.wylie.crs.entity.Student;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.entity.UserAccount;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.ProfessorRepository;
import edu.wylie.crs.repository.StudentRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第 0 步种子：三角色账号、师生档案、学期配置。班次缓存由 {@link edu.wylie.crs.service.CatalogCacheService} 从目录 Stub 同步。
 * 登录规则与教务新增师生一致：用户名=系统编号，初始密码=系统编号。
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final ProfessorRepository professorRepository;
    private final TermConfigRepository termConfigRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final PasswordEncoder passwordEncoder;
    private final String currentTerm;

    public DataSeeder(
            UserAccountRepository userAccountRepository,
            StudentRepository studentRepository,
            ProfessorRepository professorRepository,
            TermConfigRepository termConfigRepository,
            EnrollmentRepository enrollmentRepository,
            CourseOfferingRepository courseOfferingRepository,
            PasswordEncoder passwordEncoder,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.userAccountRepository = userAccountRepository;
        this.studentRepository = studentRepository;
        this.professorRepository = professorRepository;
        this.termConfigRepository = termConfigRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userAccountRepository.count() == 0) {
            seedTerms();
            seedStudents();
            seedProfessors();
            log.info("种子档案初始化完成：学期={}", currentTerm);
        }
        ensureLoginAccounts();
        log.info("登录账号已对齐为 编号/编号，用户数={}", userAccountRepository.count());
        if (!courseOfferingRepository.existsById("CS-210")) {
            seedHistoricalOfferings();
            log.info("历史学期开课快照已写入，供成绩单与录成绩");
        }
        if (enrollmentRepository.count() == 0) {
            seedHistoricalEnrollments();
            log.info("历史修课记录已写入，供先修课校验与成绩单");
        }
    }

    private void seedTerms() {
        saveTerm(currentTerm, false);
        saveTerm("2025 秋季", true);
        saveTerm("2025 春季", true);
    }

    private void saveTerm(String term, boolean closed) {
        TermConfig config = new TermConfig();
        config.setTerm(term);
        config.setRegistrationClosed(closed);
        termConfigRepository.save(config);
    }

    private void seedStudents() {
        saveStudent("S001", "Jordan Davis", "2005-03-12", "110101200503120011", "在读", "2028-06-30");
        saveStudent("S002", "李四", "2004-11-02", "110101200411020022", "在读", "2027-06-30");
        saveStudent("S003", "王五", "2005-07-21", "110101200507210033", "休学", "2028-06-30");
        saveStudent("S004", "赵六", "2005-09-30", "110101200509300044", "在读", "2028-06-30");
        saveStudent("S005", "孙七", "2004-05-18", "110101200405180055", "在读", "2027-06-30");
    }

    private void saveStudent(String id, String name, String dob, String ssn, String status, String graduationDate) {
        Student student = new Student();
        student.setId(id);
        student.setName(name);
        student.setDob(dob);
        student.setSsn(ssn);
        student.setStatus(status);
        student.setGraduationDate(graduationDate);
        studentRepository.save(student);
    }

    private void seedProfessors() {
        saveProfessor("P001", "Alex Morgan", "1980-01-15", "110101198001150044", "在职", "计算机科学");
        saveProfessor("P002", "Lin 教授", "1979-09-08", "110101197909080055", "在职", "数学");
        saveProfessor("P003", "Chen 博士", "1983-04-02", "110101198304020066", "在职", "计算机科学");
        saveProfessor("P004", "Lee 博士", "1985-12-25", "110101198512250077", "休假", "人文");
        saveProfessor("P005", "Patel 博士", "1978-06-20", "110101197806200088", "在职", "商学");
        saveProfessor("P006", "Kim 教授", "1977-03-11", "110101197703110099", "在职", "自然科学");
        saveProfessor("P007", "Dai 博士", "1982-08-19", "110101198208190010", "在职", "艺术");
    }

    private void saveProfessor(String id, String name, String dob, String ssn, String status, String dept) {
        Professor professor = new Professor();
        professor.setId(id);
        professor.setName(name);
        professor.setDob(dob);
        professor.setSsn(ssn);
        professor.setStatus(status);
        professor.setDept(dept);
        professorRepository.save(professor);
    }

    /** 师生与教务均可登录：用户名、密码均为编号（仅课程演示环境）。 */
    private void ensureLoginAccounts() {
        for (Student student : studentRepository.findAll()) {
            upsertLogin(student.getId(), student.getName(), Role.STUDENT);
        }
        for (Professor professor : professorRepository.findAll()) {
            upsertLogin(professor.getId(), professor.getName(), Role.PROFESSOR);
        }
        upsertLogin("R001", "王丽", Role.REGISTRAR);
    }

    private void upsertLogin(String id, String name, Role role) {
        UserAccount user = userAccountRepository.findById(id).orElseGet(UserAccount::new);
        boolean resetCredential = user.getId() == null || !id.equals(user.getUsername());
        user.setId(id);
        user.setUsername(id);
        if (resetCredential) {
            user.setPasswordHash(passwordEncoder.encode(id));
        }
        user.setName(name);
        user.setRole(role);
        userAccountRepository.save(user);
    }

    /** 历史学期班次：成绩单标题/学分，以及 P001 上学期待录成绩演示。 */
    private void seedHistoricalOfferings() {
        saveHistoricalOffering("CS-101", "程序设计基础", "计算机科学", "P003", "Chen 博士", "2025 春季", 3, 1);
        saveHistoricalOffering("PHYS-101", "物理基础", "自然科学", "P006", "Kim 教授", "2025 春季", 4, 1);
        saveHistoricalOffering("PHYS-201", "大学物理先修", "自然科学", "P006", "Kim 教授", "2025 春季", 4, 1);
        saveHistoricalOffering("BUS-101", "经济学导论", "商学", "P005", "Patel 博士", "2025 春季", 2, 1);
        saveHistoricalOffering("CS-210", "程序设计", "计算机科学", "P001", "Alex Morgan", "2025 秋季", 3, 5);
        saveHistoricalOffering("MATH-110", "微积分", "数学", "P001", "Alex Morgan", "2025 秋季", 4, 3);
        saveHistoricalOffering("ENG-105", "学术英语", "人文", "P004", "Lee 博士", "2025 秋季", 2, 1);
    }

    private void saveHistoricalOffering(
            String id,
            String title,
            String dept,
            String professorId,
            String professorName,
            String term,
            int credits,
            int seatsTaken
    ) {
        CourseOffering offering = new CourseOffering();
        offering.setId(id);
        offering.setCode(id);
        offering.setTitle(title);
        offering.setDept(dept);
        offering.setProfessorId(professorId);
        offering.setProfessorName(professorName);
        offering.setDaysJson("[]");
        offering.setRoom("");
        offering.setSeatsTotal(10);
        offering.setSeatsTaken(seatsTaken);
        offering.setPrerequisitesJson("[]");
        offering.setTerm(term);
        offering.setCancelled(false);
        offering.setOfferingClosed(true);
        offering.setCredits(credits);
        courseOfferingRepository.save(offering);
    }

    /**
     * 历史学期修课。先修「已满足」仅计 A/B/C/D。
     * 与前端 Mock COMPLETED={CS-101, MATH-110, ENG-105} 对齐；PHYS-201 记 F 不计入，避免 PHYS-150 因误判先修而永远 422 或误通过。
     */
    private void seedHistoricalEnrollments() {
        saveEnrollment("CS-101", "S001", "2025 春季", "A");
        saveEnrollment("PHYS-101", "S001", "2025 春季", "B");
        saveEnrollment("PHYS-201", "S001", "2025 春季", "F");
        saveEnrollment("BUS-101", "S001", "2025 春季", "C");
        saveEnrollment("CS-210", "S001", "2025 秋季", "A");
        saveEnrollment("CS-210", "S002", "2025 秋季", null);
        saveEnrollment("CS-210", "S003", "2025 秋季", "C");
        saveEnrollment("CS-210", "S004", "2025 秋季", null);
        saveEnrollment("CS-210", "S005", "2025 秋季", "B");
        saveEnrollment("MATH-110", "S001", "2025 秋季", "B");
        saveEnrollment("MATH-110", "S004", "2025 秋季", "A");
        saveEnrollment("MATH-110", "S005", "2025 秋季", null);
        saveEnrollment("ENG-105", "S001", "2025 秋季", "A");
    }

    private void saveEnrollment(String offeringId, String studentId, String term, String grade) {
        Enrollment enrollment = new Enrollment();
        enrollment.setOfferingId(offeringId);
        enrollment.setStudentId(studentId);
        enrollment.setTerm(term);
        enrollment.setGrade(grade);
        enrollmentRepository.save(enrollment);
    }

}
