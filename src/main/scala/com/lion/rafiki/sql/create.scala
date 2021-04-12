package com.lion.rafiki.sql

import doobie.implicits.toSqlInterpolator
import cats.implicits._
import doobie.implicits._

object create {
  val users =
    sql"""CREATE TABLE IF NOT EXISTS users (
       id                 serial        PRIMARY KEY,
       firstname          text,
       name               text,
       email              text          NOT NULL UNIQUE,
       password           text          NOT NULL
    )""".update

  val companies =
    sql"""CREATE TABLE IF NOT EXISTS companies (
       id                 serial        PRIMARY KEY,
       name               text          NOT NULL,
       rh_user            serial        NOT NULL references users(id)
    )""".update

  val companyContracts =
    sql"""CREATE TABLE IF NOT EXISTS company_contracts (
       id                 serial        PRIMARY KEY,
       company            serial        NOT NULL references companies(id),
       kind               text          NOT NULL
    )""".update

  val forms =
    sql"""
    CREATE TABLE IF NOT EXISTS forms (
       id                 serial        PRIMARY KEY,
       company            serial        references companies(id),
       name               text          NOT NULL,
       description        text,
       tree_id            serial,
       tree_kind          form_tree_constr,
       FOREIGN KEY (tree_id, tree_kind)
          REFERENCES form_trees(id, kind)
          ON DELETE SET NULL
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
       id                 serial        NOT NULL,
       kind               form_tree_constr NOT NULL,
       parent_id          serial,
       parent_kind        form_tree_constr,
       PRIMARY KEY (id, kind),
       FOREIGN KEY (parent_id, parent_kind)
          REFERENCES form_trees (id, kind)
          ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS form_tree_questions (
       id INTEGER PRIMARY KEY,
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
    CREATE TABLE IF NOT EXISTS form_tree_groups (
       id INTEGER PRIMARY KEY,
       kind
          form_tree_constr NOT NULL
          DEFAULT 'group'
          CHECK (kind = 'group'),

       FOREIGN KEY (id, kind)
          REFERENCES form_trees (id, kind)
          ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS form_tree_texts (
       id INTEGER PRIMARY KEY,
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

  val allTables = (users.run, companies.run, companyContracts.run, formTrees.run, forms.run).mapN(_ + _ + _ + _ + _)
}
