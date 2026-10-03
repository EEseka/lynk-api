-- Photos people add to a hangout's album once it is completed.
--
-- A photo starts PENDING when its upload slot is handed out and turns READY once the files are
-- confirmed in storage. PENDING rows count toward the per-person cap, so two uploads racing each
-- other cannot slip past it, and a sweep deletes the ones whose upload never finished.
--
-- Storage paths are not stored: they are always {hangout_id}/{id}/full.jpg and thumb.jpg, built
-- by the server, so a client can never choose where its bytes land.
--
-- No ON DELETE CASCADE on either key. Photos own files in storage, and a cascade would drop the
-- rows while leaving the files behind; deleting a hangout or a user must go through the code that
-- removes both.
CREATE TABLE hangout_service.hangout_photos (
    id uuid NOT NULL,
    hangout_id uuid NOT NULL,
    uploader_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    caption character varying(200),
    created_at timestamp(6) with time zone NOT NULL,
    confirmed_at timestamp(6) with time zone,
    CONSTRAINT hangout_photos_pkey PRIMARY KEY (id),
    CONSTRAINT hangout_photos_status_check CHECK (status IN ('PENDING', 'READY')),
    CONSTRAINT fk_hangout_photos_hangout FOREIGN KEY (hangout_id)
        REFERENCES hangout_service.hangouts (id),
    CONSTRAINT fk_hangout_photos_uploader FOREIGN KEY (uploader_id)
        REFERENCES hangout_service.hangout_users (user_id)
);

-- The album page and the per-person count both start from the hangout.
CREATE INDEX idx_hangout_photos_hangout_id_created_at
    ON hangout_service.hangout_photos (hangout_id, created_at DESC);

-- Deleting an account removes every photo that person uploaded.
CREATE INDEX idx_hangout_photos_uploader_id
    ON hangout_service.hangout_photos (uploader_id);

-- The sweep only ever looks for old PENDING rows.
CREATE INDEX idx_hangout_photos_pending_created_at
    ON hangout_service.hangout_photos (created_at)
    WHERE status = 'PENDING';
