-- V0: Extensions + Schemas
-- Run once. Sets up gen_random_uuid() and the 4 module namespaces.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS stay;
CREATE SCHEMA IF NOT EXISTS restaurant;
CREATE SCHEMA IF NOT EXISTS expense;