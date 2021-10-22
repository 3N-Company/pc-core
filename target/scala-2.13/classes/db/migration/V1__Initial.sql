CREATE
EXTENSION pgcrypto;
CREATE TYPE role AS ENUM ('plain', 'admin');

CREATE TABLE users
(
    id       uuid NOT NULL DEFAULT gen_random_uuid(),
    username text NOT NULL,
    password text NOT NULL,
    u_role role NOT NULL DEFAULT 'plain',
    CONSTRAINT PK_9 PRIMARY KEY ( "id" ),
    CONSTRAINT ind_51 UNIQUE ( username )
);

INSERT INTO users (username, password) VALUES (
 'admin',
  crypt('admin', gen_salt('bf'))
);

CREATE TABLE photo
(
    id   serial NOT NULL,
    file_path varchar(50) NOT NULL,
    CONSTRAINT PK_5 PRIMARY KEY ( "id" )
);


CREATE TABLE sessions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  key text NOT NULL DEFAULT gen_salt('md5'),
  CONSTRAINT PK_15 PRIMARY KEY ("id"),
  CONSTRAINT FK_40 FOREIGN KEY ( user_id ) REFERENCES users ("id")
);

CREATE INDEX sessions_cookie_idx ON sessions(
 (id || '-' || encode(hmac(id::text, "key", 'sha256'), 'hex'))
);


CREATE TABLE submission
(
    photo_id integer NOT NULL,
    user_id  uuid NOT NULL,
    name     text NULL,
    CONSTRAINT PK_53 PRIMARY KEY ( photo_id, user_id ),
    CONSTRAINT FK_28 FOREIGN KEY ( photo_id ) REFERENCES photo ( "id" ),
    CONSTRAINT FK_31 FOREIGN KEY ( user_id ) REFERENCES users ( "id" )
    );

CREATE INDEX fkIdx_30 ON submission
    (
    photo_id
    );

CREATE INDEX fkIdx_33 ON submission
    (
    user_id
    );
