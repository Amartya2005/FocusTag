-- C-path: IoT ingest stub table only (firmware deferred)

CREATE TABLE IF NOT EXISTS public.iot_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT UNIQUE NOT NULL,
    location_id UUID REFERENCES public.locations(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.iot_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL,
    tag_uid TEXT,
    student_hint TEXT,
    event TEXT NOT NULL CHECK (event IN ('scan', 'heartbeat', 'error')),
    raw JSONB,
    ts TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_iot_events_device_ts ON public.iot_events (device_id, ts DESC);

ALTER TABLE public.iot_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.iot_events ENABLE ROW LEVEL SECURITY;
-- No anon write policies yet — Edge Function service role only until device auth lands.
