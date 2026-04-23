package com.bayport.service;

import com.bayport.entity.Doctor;
import com.bayport.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    public List<Doctor> list() {
        return doctorRepository.findAll();
    }

    public Doctor get(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
    }

    public Doctor create(Doctor doctor) {
        return doctorRepository.save(doctor);
    }

    public Doctor update(Long id, Doctor doctor) {
        Doctor existing = get(id);
        existing.setFullName(doctor.getFullName());
        existing.setEmail(doctor.getEmail());
        existing.setPhone(doctor.getPhone());
        existing.setSpecialty(doctor.getSpecialty());
        existing.setLicenseNo(doctor.getLicenseNo());
        existing.setHourlyRate(doctor.getHourlyRate());
        return doctorRepository.save(existing);
    }

    public void delete(Long id) {
        doctorRepository.deleteById(id);
    }
}

