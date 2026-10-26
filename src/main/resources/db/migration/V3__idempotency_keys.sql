create table idempotency_keys (
    scope           varchar(64)  not null,
    idempotency_key varchar(128) not null,
    request_hash    varchar(64)  not null,
    response_body   text         not null,
    created_at      timestamptz  not null,
    primary key (scope, idempotency_key)
);

create index idempotency_keys_created_idx on idempotency_keys (created_at);
