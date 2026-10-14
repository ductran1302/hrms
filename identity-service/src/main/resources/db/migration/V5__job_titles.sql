-- ============================================================
-- Danh mục chức vụ (job titles) — tham chiếu cho dropdown "Chức danh".
-- Seed từ DataSeeder (idempotent theo code).
-- ============================================================

create table job_titles
(
    id         uuid         not null primary key,
    code       varchar(30)  not null,
    name       varchar(100) not null,
    name_en    varchar(100) not null,
    category   varchar(30)  not null,
    sort_order int          not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    version    bigint       not null default 0,
    constraint uq_job_titles_code unique (code)
);

create index idx_job_titles_category on job_titles (category);
