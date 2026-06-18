-- Seed demo fleet vehicles
INSERT INTO vehicles (vehicle_number, brand, model, type, status, current_mileage,
                      last_service_date, next_service_date, next_service_mileage,
                      insurance_expiry, assigned_driver_id, created_at, updated_at)
VALUES
  ('FL-001', 'Toyota',     'Hiace',     'VAN',   'ACTIVE',   42000, '2024-09-10', '2025-03-10', 47000, '2025-12-31', 'driver1',  NOW(), NOW()),
  ('FL-002', 'Ford',       'Transit',   'VAN',   'ACTIVE',   61500, '2024-08-05', '2025-02-05', 66500, '2025-08-15', NULL,       NOW(), NOW()),
  ('FL-003', 'Tata',       'Ace',       'TRUCK', 'ACTIVE',   98000, '2024-11-20', '2025-05-20', 103000,'2026-01-10', NULL,       NOW(), NOW()),
  ('FL-004', 'Mahindra',   'Bolero',    'SUV',   'ACTIVE',   34500, '2025-01-15', '2025-07-15', 39500, '2025-09-30', NULL,       NOW(), NOW()),
  ('FL-005', 'Toyota',     'Innova',    'SUV',   'ACTIVE',   22000, '2025-02-01', '2025-08-01', 27000, '2026-03-31', NULL,       NOW(), NOW()),
  ('FL-006', 'Ashok Leyland','Dost',    'TRUCK', 'MAINTENANCE',115000,'2024-07-12','2024-12-12',120000,'2025-06-30', NULL,       NOW(), NOW()),
  ('FL-007', 'Maruti',     'Eeco',      'VAN',   'ACTIVE',   18500, '2025-03-01', '2025-09-01', 23500, '2026-06-15', NULL,       NOW(), NOW()),
  ('FL-008', 'Isuzu',      'D-Max',     'TRUCK', 'ACTIVE',   77000, '2024-10-18', '2025-04-18', 82000, '2025-11-20', NULL,       NOW(), NOW()),
  ('FL-009', 'Honda',      'City',      'CAR',   'INACTIVE', 54000, '2024-06-22', '2025-01-22', 59000, '2025-07-31', NULL,       NOW(), NOW()),
  ('FL-010', 'Hyundai',    'H1',        'VAN',   'ACTIVE',   30000, '2025-01-10', '2025-07-10', 35000, '2026-02-28', NULL,       NOW(), NOW())
ON CONFLICT (vehicle_number) DO NOTHING;
