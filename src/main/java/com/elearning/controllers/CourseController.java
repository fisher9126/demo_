package com.elearning.controllers;

import com.elearning.models.entities.Course;
import com.elearning.models.repositories.CourseRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRepository courseRepository;

    @GetMapping
    public ResponseEntity<com.elearning.advice.ApiResponse<List<Course>>> getAllCourses() {
        return ResponseEntity.ok(com.elearning.advice.ApiResponse.success(courseRepository.findAll(),
                "Fetched all courses successfully"));
    }
}
