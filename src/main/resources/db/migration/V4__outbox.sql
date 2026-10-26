create table outbox_events (
    id             uuid          primary key,
    aggregate_type varchar(64)   not null,
    aggregate_id   uuid          not null,
    event_type     varchar(64)   not null,
    payload        jsonb         not null,
    created_at     timestamptz   not null,
    published_at   timestamptz,
    attempts       int           not null default 0,
    last_error     varchar(1024)
);

-- the relay only ever scans unpublished rows, so keep that index small
create index outbox_events_unpublished_idx on outbox_events (created_at) where published_at is null;
