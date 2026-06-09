  # TEST PLAN
  ## Bayport Veterinary Clinic Management System (VCMS)

  ## Abstract
  This document defines the scope, approach, resources, schedule, and deliverables for testing the Bayport Veterinary Clinic Management System (VCMS). It aligns with Bayport requirement IDs REQ-001 to REQ-022 and covers functional, security, availability, performance, usability, and deployment constraints for web and desktop operation with Spring Boot backend and MySQL/H2 database options.

  ---

  ## Table of Contents
  - Introduction
  - Application Overview
  - 1 Test Strategy
    - 1.1 Test Type
    - 1.2 Scope of Testing
      - 1.2.1 Features to be Tested
      - 1.2.2 Features not to be Tested
    - 1.3 Risk and Issues
    - 1.4 Test Logistics
      - 1.4.1 Who will test?
      - 1.4.2 When will test occur?
  - 2 Test Objective
  - 3 Test Criteria
    - 3.1 Suspension Criteria
    - 3.2 Exit Criteria
  - 4 Resource Planning
    - 4.1 System Resource
    - 4.2 Human Resource
  - 5 Test Environment
  - 6 Schedule and Estimation
    - 6.1 All project tasks and estimation
    - 6.2 Schedule to complete tasks
  - 7 Test Deliverables
    - 7.1 Before testing phase
    - 7.2 During testing
    - 7.3 After testing cycles

  ---

  ## Introduction
  This Test Plan describes the testing activities for the Bayport Veterinary Clinic Management System. The plan identifies the features to be tested, test levels to be executed, criteria for suspension and completion, and required personnel and infrastructure.

  The purpose of testing is to verify that the system reliably supports clinic operations such as role-based login, pet records, appointment workflows, prescriptions, reports, reminders, audit logs, POS/dispensing, recycle bin operations, and desktop deployment behavior.

  ## Application Overview
  Bayport VCMS is a clinic operations platform with:
  - Web frontend for admin and staff workflows
  - Spring Boot REST backend
  - Desktop wrapper option for clinic deployment
  - Relational database support (MySQL for shared deployments; embedded option for desktop/offline mode)
  - Role-based access controls for clinic users

  ---

  ## 1 Test Strategy

  ### 1.1 Test Type
  The following testing types will be conducted:

  - **Unit Testing**: Validate individual services/controllers/utilities (auth, scheduling logic, POS discount computation, reminder creation, role checks).
  - **Integration Testing**: Validate API-to-database and module-to-module flows (login, appointments, inventory-to-sale updates, audit logging).
  - **System Testing**: Validate complete end-to-end workflows from UI to backend to database.
  - **Security Testing (functional security checks)**: Validate authorization boundaries, endpoint protection, invalid input handling, and session/access constraints.
  - **User Acceptance Testing (UAT)**: Validate real clinic process fit for front office, vet, and admin workflows.

  ### 1.2 Scope of Testing

  #### 1.2.1 Features to be Tested
  All in-scope requirements REQ-001 to REQ-022 are included. Core feature groups:

  | Module | Applicable Roles | Coverage |
  |---|---|---|
  | Authentication and Roles | Admin, Vet, Receptionist, Pharmacist | Login, role enforcement, admin account management, MFA option, password reset |
  | Pet Records | Admin, Vet, Receptionist | CRUD with owner linkage and optional photos, search/filter/list/grid |
  | Appointments | Admin, Vet, Receptionist | Slot scheduling, assignment, status flow, slot capacity enforcement |
  | Prescriptions and Dispensing | Vet, Pharmacist, Front Office | Prescription capture (dosage, printable), sale/dispense endpoint validation |
  | Reports and Exports | Admin | Operational reports with charts and PDF output |
  | Reminders and Notifications | Admin, Front Office | Template-based reminders, calendar views, optional email sends, in-app alerts |
  | Audit and Data Retention | Admin | Access/activity logs with actor/IP/context, recycle bin restore/permanent delete |
  | Security and API Protection | All roles | Role-protected screens and endpoints, invalid request handling |
  | Deployment Profiles | Admin/IT | Desktop embedded DB mode and MySQL mode behavior |
  | Performance and Usability | All users | Responsiveness under normal concurrent load, intuitive and consistent UI |

  #### 1.2.2 Features not to be Tested
  The following are out of scope for this test cycle:
  - Penetration testing and third-party security certification
  - Hardware failure simulation beyond standard desktop/laptop behavior
  - OS-level hardening policies and enterprise domain controls
  - Multi-region/high-availability cloud failover testing
  - Mobile app testing (if not part of this release)

  ### 1.3 Risk and Issues

  | Risk | Mitigation |
  |---|---|
  | Incorrect or missing MySQL credentials may block backend startup | Use scripted setup, environment-variable checks, pre-flight DB connectivity test |
  | Requirement changes during test execution | Baseline requirements and control changes through documented change requests |
  | Test data inconsistency across cycles | Use seeded test datasets and controlled reset scripts |
  | Role misconfiguration causing false failures | Maintain role-permission matrix and validated test accounts |
  | Limited UAT availability from clinic staff | Schedule UAT windows early and prepare guided scripts |

  ### 1.4 Test Logistics

  #### 1.4.1 Who will test?
  - QA/Test Lead: owns strategy, entry/exit decisions, reporting
  - QA Testers: execute test cases and regression suites
  - Developers: triage defects, fix issues, support retesting
  - Product/Clinic Representatives: perform UAT and business validation

  #### 1.4.2 When will test occur?
  Testing starts when:
  - Approved requirements and traceability matrix are available
  - Build is deployable for target profile(s) (desktop and/or MySQL)
  - Test cases and datasets are prepared and reviewed
  - Test environment and accounts are ready

  ---

  ## 2 Test Objective
  The objectives are:
  - Verify that each requirement REQ-001 to REQ-022 is implemented and testable
  - Validate critical clinic workflows end-to-end without major defects
  - Ensure role and endpoint protections work as intended
  - Confirm stable operation in both desktop-style and MySQL-backed execution profiles
  - Provide release readiness evidence through measurable test results

  ---

  ## 3 Test Criteria

  ### 3.1 Suspension Criteria
  Testing is suspended when any of these occur:
  - A blocker defect prevents testing of a critical flow (login, patient record save, appointment booking, checkout/dispense)
  - Build instability causes repeated environment failures
  - More than 35% of executed critical test cases fail in a cycle

  ### 3.2 Exit Criteria
  Testing phase is complete when:
  - 100% of planned critical test cases are executed
  - At least 90% overall pass rate is achieved
  - No open Critical defects and no open High defects without approved workaround/risk acceptance
  - Requirement coverage for REQ-001 to REQ-022 is documented in the test report

  ---

  ## 4 Resource Planning

  ### 4.1 System Resource

  | No. | Resource | Description |
  |---|---|---|
  | 1 | Test Workstations | Windows desktops/laptops for web and desktop app execution |
  | 2 | Backend Runtime | Java 17 + Maven for Spring Boot backend |
  | 3 | Database | MySQL 8.x instance and embedded DB profile for desktop mode |
  | 4 | Browser | Chromium-based browser for frontend testing |
  | 5 | Test Data | Seeded users, pets, appointments, products, prescriptions, sales records |
  | 6 | Reporting Tools | Defect tracker, test case sheets, export verification tools (CSV/PDF) |

  ### 4.2 Human Resource

  | No. | Member | Tasks |
  |---|---|---|
  | 1 | QA/Test Lead | Plan ownership, execution control, metrics, final sign-off recommendation |
  | 2 | QA Engineer(s) | Manual/API testing, bug reporting, retesting, regression |
  | 3 | Developer(s) | Defect fixing, technical support, log analysis |
  | 4 | UAT Representative(s) | Business process validation and acceptance feedback |

  ---

  ## 5 Test Environment
  Baseline test environment:
  - OS: Windows 10/11
  - Backend: Spring Boot (Java 17, Maven)
  - Frontend: HTML/CSS/JS client
  - DB Profiles:
    - `desktop,localmysql` using local MySQL
    - fallback local profile for embedded DB validation
  - Network: localhost deployment for desktop/in-clinic validation
  - Key Config:
    - `SPRING_DATASOURCE_URL`
    - `SPRING_DATASOURCE_USERNAME`
    - `SPRING_DATASOURCE_PASSWORD`
    - optional `SPRING_MAIL_*` for reminder email tests

  ---

  ## 6 Schedule and Estimation

  ### 6.1 All project tasks and estimation

  | Task | Members | Estimated Effort |
  |---|---|---|
  | Test plan and test design | QA Lead + QA | 24 man-hours |
  | Test case development and traceability | QA | 36 man-hours |
  | Environment setup and test data prep | QA + Dev | 16 man-hours |
  | Functional and integration execution | QA | 56 man-hours |
  | Security/role/access checks | QA + Dev | 20 man-hours |
  | Regression cycle(s) | QA | 24 man-hours |
  | UAT support and fixes verification | QA + Dev + UAT reps | 24 man-hours |
  | Final reporting and release recommendation | QA Lead | 12 man-hours |
  | **Total** |  | **212 man-hours** |

  ### 6.2 Schedule to complete tasks

  | Week | Planned Activities |
  |---|---|
  | Week 1 | Test planning, test case design, traceability matrix completion |
  | Week 2 | Environment setup, smoke tests, first full execution cycle |
  | Week 3 | Defect retest, regression, security/role validation |
  | Week 4 | UAT execution, final fixes verification, closure report |

  ---

  ## 7 Test Deliverables

  ### 7.1 Before testing phase
  - Approved Requirements Specification / Requirement Matrix (REQ-001 to REQ-022)
  - Test Plan document
  - Test case specifications
  - Test data and account matrix

  ### 7.2 During testing
  - Test execution logs
  - Defect reports with severity and status
  - Requirement traceability updates
  - Daily/weekly test progress reports

  ### 7.3 After the testing cycles is over
  - Final test summary report
  - Signed-off requirement traceability matrix
  - Defect closure report and known issues list
  - Release readiness recommendation

  ---

  ## Requirement Traceability Summary (Bayport)

  | Requirement ID | Category | Validation Focus | Primary Test Level |
  |---|---|---|---|
  | REQ-001 | Functional | Authentication + role access (Admin/Vet/Receptionist/Pharmacist) | System, Security |
  | REQ-002 | Functional | Admin user and role management | Integration, System |
  | REQ-003 | Functional | Optional MFA via email OTP | Integration, System |
  | REQ-004 | Functional | Password reset flow | System |
  | REQ-005 | Functional | Pet records with owner linkage and photos | Integration, System |
  | REQ-006 | Functional | Search/filter/list/grid pet views | System, Usability |
  | REQ-007 | Functional | Appointment assignment, slots, status workflow | Integration, System |
  | REQ-008 | Functional | Slot capacity enforcement | Unit, Integration |
  | REQ-009 | Functional | Prescriptions with dosage and printable output | System |
  | REQ-010 | Functional | Operational reports with charts/PDF | System |
  | REQ-011 | Functional | Reminder templates/calendar/email scheduling | Integration, System |
  | REQ-012 | Functional | Data-access logs with actor/IP/context | Integration, System |
  | REQ-013 | Functional | Recycle bin soft-delete/restore/permanent delete | Integration, System |
  | REQ-014 | Functional | In-app notifications | System |
  | REQ-015 | Security | Role protection for APIs and screens | Security, System |
  | REQ-016 | Security | Sale/dispense endpoint validation against invalid data | Unit, Integration |
  | REQ-017 | Availability | Embedded DB desktop self-contained mode | System |
  | REQ-018 | Availability | MySQL deployment support | Integration, System |
  | REQ-019 | Performance | Responsive behavior under concurrent use | Performance, System |
  | REQ-020 | Usability | Consistent, intuitive UI | Usability, UAT |
  | REQ-021 | Design Constraint | Cross-platform desktop app wrapping web UI + local backend | System |
  | REQ-022 | Design Constraint | Align with common clinic Windows setup | System, UAT |
