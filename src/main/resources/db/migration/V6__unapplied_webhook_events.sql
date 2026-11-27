-- Authentic provider events that could not be applied (unknown payout, out-of-order or
-- contradictory status). They are acknowledged so the provider stops redelivering, and
-- kept here so reconciliation can see them instead of losing them in a log line.
create table unapplied_webhook_events (
    id                 uuid          primary key,
    provider           varchar(32)   not null,
    event_type         varchar(64)   not null,
    payout_id          uuid,
    provider_payout_id varchar(64),
    reason             varchar(512)  not null,
    payload            text          not null,
    received_at        timestamptz   not null
);

create index unapplied_webhook_events_payout_idx on unapplied_webhook_events (payout_id);
