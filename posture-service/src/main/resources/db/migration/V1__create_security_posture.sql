CREATE TABLE IF NOT EXISTS security_posture (
  service_id       VARCHAR(80) NOT NULL,
  environment      VARCHAR(16) NOT NULL,
  internet_facing  BOOLEAN NOT NULL DEFAULT false,
  authn            JSONB,
  data             JSONB,
  tls              JSONB,
  network          JSONB,
  secrets          JSONB,
  vulns            JSONB,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (service_id, environment)
);
