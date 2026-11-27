CREATE TABLE public_storefront (
    id INTEGER PRIMARY KEY,
    version BIGINT NOT NULL,
    draft_json CLOB,
    published_json CLOB
);
INSERT INTO public_storefront (id, version) VALUES (1, 0);
