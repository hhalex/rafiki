package com.lion.rafiki.sql

import cats.implicits._
import doobie.implicits._

object create {
  val users =
    sql"""CREATE TABLE IF NOT EXISTS users (
       id                 bigserial        PRIMARY KEY,
       firstname          text,
       name               text,
       email              text          NOT NULL UNIQUE,
       password           text          NOT NULL
    )""".update

  val companies =
    sql"""CREATE TABLE IF NOT EXISTS companies (
       id                 bigserial        PRIMARY KEY,
       name               text          NOT NULL,
       rh_user            bigint        NOT NULL references users(id)
    )""".update

  val companyContracts =
    sql"""CREATE TABLE IF NOT EXISTS company_contracts (
       id                 bigserial        PRIMARY KEY,
       company            bigint           NOT NULL references companies(id),
       kind               text             NOT NULL
    )""".update

  val forms =
    sql"""
    CREATE TABLE IF NOT EXISTS forms (
       id                 bigserial        PRIMARY KEY,
       company            bigint           references companies(id),
       name               text             NOT NULL,
       description        text,
       tree_id            bigint,
       tree_kind          form_tree_constr,
       FOREIGN KEY (tree_id, tree_kind)
          REFERENCES form_trees(id, kind)
          ON DELETE SET NULL,
       CONSTRAINT Company_form_name UNIQUE (company, name)
    )""".update

  val formTrees =
    sql"""
    DO $$$$ BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'form_tree_constr') THEN
        CREATE TYPE form_tree_constr AS ENUM ('group', 'question', 'text');
      END IF;
      --more types here...
    END $$$$;

    CREATE TABLE IF NOT EXISTS form_trees (
       id                 bigserial        NOT NULL,
       kind               form_tree_constr NOT NULL,
       parent_id          bigint,
       parent_kind        form_tree_constr,
       PRIMARY KEY (id, kind),
       FOREIGN KEY (parent_id, parent_kind)
          REFERENCES form_trees (id, kind)
          ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS form_tree_questions (
       id bigint PRIMARY KEY,
       kind
          form_tree_constr NOT NULL
          DEFAULT 'question'
          CHECK (kind = 'question'),

       label TEXT NOT NULL,
       text TEXT NOT NULL,

       FOREIGN KEY (id, kind)
          REFERENCES form_trees (id, kind)
          ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS form_tree_question_answers (
       id bigserial PRIMARY KEY,
       question_id bigint NOT NULL,

       label text,

       num_value integer,

       FOREIGN KEY (question_id)
          REFERENCES form_tree_questions (id)
          ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS form_tree_groups (
       id bigint PRIMARY KEY,
       kind
          form_tree_constr NOT NULL
          DEFAULT 'group'
          CHECK (kind = 'group'),

       FOREIGN KEY (id, kind)
          REFERENCES form_trees (id, kind)
          ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS form_tree_texts (
       id bigint PRIMARY KEY,
       kind
          form_tree_constr NOT NULL
          DEFAULT 'text'
          CHECK (kind = 'text'),

       text TEXT NOT NULL,

       FOREIGN KEY (id, kind)
          REFERENCES form_trees (id, kind)
          ON DELETE CASCADE
    )
    """.update

  val formSessions =
    sql"""
    CREATE TABLE IF NOT EXISTS form_sessions (
       id                     bigserial         PRIMARY KEY,
       company_contract_id    bigint            NOT NULL,
       form_id                bigint            NOT NULL,
       name                   text              NOT NULL,
       start_date             TIMESTAMP,
       end_date               TIMESTAMP,
       FOREIGN KEY (company_contract_id)
          REFERENCES company_contracts(id),
       FOREIGN KEY (form_id)
          REFERENCES forms(id),
       CONSTRAINT CK_start_stop
          CHECK (
               (start_date IS NULL AND end_date IS NULL)
            OR (start_date IS NOT NULL AND end_date IS NULL)
            OR (start_date IS NOT NULL AND end_date IS NOT NULL)
          )
    )""".update

  val formSessionInvites =
    sql"""
    CREATE TABLE IF NOT EXISTS form_session_invites (
       id                     bigserial         PRIMARY KEY,
       form_session_id        bigint            NOT NULL,
       user_id                bigint            NOT NULL,
       team                   text              NOT NULL DEFAULT '/',
       accept_conditions      boolean,
       FOREIGN KEY (form_session_id)
          REFERENCES form_sessions(id),
       FOREIGN KEY (user_id)
          REFERENCES users(id),
       UNIQUE(form_session_id, user_id)
    )""".update

  val allTables = (users.run, companies.run, companyContracts.run, formTrees.run, forms.run, formSessions.run, formSessionInvites.run).mapN(_ + _ + _ + _ + _ + _ + _)
}
