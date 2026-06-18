-- Fix invalid VehicleStatus values inserted by V2 and V4 seed migrations.
-- Java enum VehicleStatus only has: ACTIVE, IN_SERVICE, BREAKDOWN, RETIRED
-- Seeds incorrectly used MAINTENANCE (-> IN_SERVICE) and INACTIVE (-> RETIRED).
UPDATE vehicles SET status = 'IN_SERVICE' WHERE status = 'MAINTENANCE';
UPDATE vehicles SET status = 'RETIRED'    WHERE status = 'INACTIVE';
