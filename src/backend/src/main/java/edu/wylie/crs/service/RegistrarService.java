package edu.wylie.crs.service;

import edu.wylie.crs.dto.RegistrarDtos.CloseResultVO;
import edu.wylie.crs.dto.RegistrarDtos.ProfessorUpsertRequest;
import edu.wylie.crs.dto.RegistrarDtos.ProfessorVO;
import edu.wylie.crs.dto.RegistrarDtos.StudentUpsertRequest;
import edu.wylie.crs.dto.RegistrarDtos.StudentVO;

import java.util.List;

public interface RegistrarService {
    List<StudentVO> listStudents();
    StudentVO addStudent(StudentUpsertRequest request);
    void updateStudent(String id, StudentUpsertRequest request);
    void deleteStudent(String id);

    List<ProfessorVO> listProfessors();
    ProfessorVO addProfessor(ProfessorUpsertRequest request);
    void updateProfessor(String id, ProfessorUpsertRequest request);
    void deleteProfessor(String id);

    CloseResultVO closeRegistration();
}
