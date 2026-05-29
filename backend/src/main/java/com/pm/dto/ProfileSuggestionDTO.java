package com.pm.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileSuggestionDTO {
    private Personal personal;
    private String headline;
    private String summary;
    private Skills skills;
    private List<Experience> experience;
    private List<Education> education;
    private List<Certification> certifications;
    private List<Project> projects;
    private Meta meta;

    @Data
    public static class Personal {
        private String fullName;
        private String email;
        private String phone;
        private String location;
        private String linkedIn;
        private String portfolio;
        private String github;
    }

    @Data
    public static class Skills {
        private List<String> technical;
        private List<String> soft;
        private List<String> languages;
    }

    @Data
    public static class Experience {
        private String title;
        private String company;
        private String location;
        private String startDate;
        private String endDate;
        private boolean isCurrent;
        private String description;
        private List<String> achievements;
    }

    @Data
    public static class Education {
        private String degree;
        private String field;
        private String institution;
        private String startDate;
        private String endDate;
        private String grade;
    }

    @Data
    public static class Certification {
        private String name;
        private String issuer;
        private String date;
        private String url;
    }

    @Data
    public static class Project {
        private String name;
        private String description;
        private List<String> technologies;
        private String url;
    }

    @Data
    public static class Meta {
        private int confidence;
        private List<String> missingFields;
        private List<String> suggestions;
    }
}
