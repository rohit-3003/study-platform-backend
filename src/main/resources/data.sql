-- Seed data: Subjects for each exam type
-- UPSC Subjects
INSERT INTO subjects (exam_type, name, description, created_at) VALUES
('UPSC', 'Indian Polity', 'Constitution, governance, and political system', NOW()),
('UPSC', 'Indian History', 'Ancient, medieval, and modern Indian history', NOW()),
('UPSC', 'Geography', 'Indian and world geography', NOW()),
('UPSC', 'Indian Economy', 'Economic development and planning', NOW()),
('UPSC', 'General Science', 'Physics, chemistry, biology basics', NOW()),
('UPSC', 'Current Affairs', 'National and international current events', NOW()),
('UPSC', 'Environment & Ecology', 'Environmental issues and biodiversity', NOW()),
('UPSC', 'Ethics & Integrity', 'Ethics, aptitude, and integrity in public life', NOW());

-- SSC Subjects
INSERT INTO subjects (exam_type, name, description, created_at) VALUES
('SSC', 'Quantitative Aptitude', 'Mathematics and numerical ability', NOW()),
('SSC', 'English Language', 'Grammar, vocabulary, and comprehension', NOW()),
('SSC', 'General Intelligence', 'Reasoning and logical ability', NOW()),
('SSC', 'General Awareness', 'Static GK and current affairs', NOW());

-- Banking Subjects
INSERT INTO subjects (exam_type, name, description, created_at) VALUES
('BANKING', 'Quantitative Aptitude', 'Data interpretation and number systems', NOW()),
('BANKING', 'Reasoning Ability', 'Logical and analytical reasoning', NOW()),
('BANKING', 'English Language', 'Reading comprehension and grammar', NOW()),
('BANKING', 'General Awareness', 'Banking awareness and current affairs', NOW()),
('BANKING', 'Computer Knowledge', 'Basic computer concepts and terminology', NOW());

-- State Govt Subjects
INSERT INTO subjects (exam_type, name, description, created_at) VALUES
('STATE_GOV', 'General Studies', 'History, geography, polity, and economy', NOW()),
('STATE_GOV', 'Quantitative Aptitude', 'Mathematics and data analysis', NOW()),
('STATE_GOV', 'Reasoning', 'Verbal and non-verbal reasoning', NOW()),
('STATE_GOV', 'Regional Language', 'State official language proficiency', NOW()),
('STATE_GOV', 'General English', 'English language skills', NOW());
