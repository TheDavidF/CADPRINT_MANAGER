CREATE TABLE public_storefront (
    id INTEGER PRIMARY KEY,
    version BIGINT NOT NULL,
    draft_json MEDIUMTEXT,
    published_json MEDIUMTEXT
);
INSERT INTO public_storefront (id, version) VALUES (1, 0);
