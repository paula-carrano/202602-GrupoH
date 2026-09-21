INSERT INTO players (first_name, last_name, birth_date, nationality, position, current_team, league, active, created_at, updated_at)
VALUES 
('Lionel', 'Messi', '1987-06-24', 'Argentina', 'FORWARD', 'Inter Miami', 'Major League Soccer', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Erling', 'Haaland', '2000-07-21', 'Noruega', 'FORWARD', 'Manchester City', 'Premier League', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Jude', 'Bellingham', '2003-06-29', 'Inglaterra', 'MIDFIELDER', 'Real Madrid', 'La Liga', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Harry', 'Kane', '1993-07-28', 'Inglaterra', 'FORWARD', 'Bayern Munich', 'Bundesliga', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Lautaro', 'Martínez', '1997-08-22', 'Argentina', 'FORWARD', 'Inter Milan', 'Serie A', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Kylian', 'Mbappé', '1998-12-20', 'Francia', 'FORWARD', 'Real Madrid', 'La Liga', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Juvenil', 'Promesa', NULL, 'Argentina', 'MIDFIELDER', 'Club Atlético Banfield', 'Liga Profesional', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
