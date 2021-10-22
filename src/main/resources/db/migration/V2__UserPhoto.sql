CREATE TABLE user_photo (
    user_id uuid NOT NULL,
    last_photo integer NOT NULL,
    CONSTRAINT PK_70 PRIMARY KEY (user_id),
    CONSTRAINT FK_71 FOREIGN KEY (user_id) REFERENCES users ("id"),
    CONSTRAINT FK_72 FOREIGN KEY (last_photo) REFERENCES photo ("id")
);
