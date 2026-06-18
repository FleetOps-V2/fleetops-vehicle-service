-- Add FL-011 to FL-015; brings total fleet to 15 vehicles
-- FL-011 assigned to driver2, FL-012 assigned to driver3
INSERT INTO vehicles (vehicle_number, brand, model, type, status, current_mileage,
                      last_service_date, next_service_date, next_service_mileage,
                      insurance_expiry, assigned_driver_id, created_at, updated_at)
VALUES
  ('FL-011', 'Toyota',   'Land Cruiser', 'SUV',   'ACTIVE',      15000, '2025-04-01', '2025-10-01', 20000, '2026-09-30', 'driver2', NOW(), NOW()),
  ('FL-012', 'Ford',     'Ranger',       'TRUCK', 'ACTIVE',      48000, '2024-12-10', '2025-06-10', 53000, '2026-05-31', 'driver3', NOW(), NOW()),
  ('FL-013', 'Tata',     'Xenon',        'TRUCK', 'ACTIVE',      72000, '2024-09-25', '2025-03-25', 77000, '2025-10-15', NULL,      NOW(), NOW()),
  ('FL-014', 'Mahindra', 'Scorpio',      'SUV',   'MAINTENANCE',  9000, '2025-01-20', '2025-07-20', 14000, '2026-01-31', NULL,      NOW(), NOW()),
  ('FL-015', 'Ashok Leyland','Partner',  'VAN',   'ACTIVE',      55000, '2025-02-14', '2025-08-14', 60000, '2025-12-20', NULL,      NOW(), NOW())
ON CONFLICT (vehicle_number) DO NOTHING;
