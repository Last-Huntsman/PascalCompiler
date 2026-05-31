program OptimizerFlow;

var
  total: integer;

function pick: integer;
begin
  if true then
  begin
    pick := 7;
    exit;
    pick := 99
  end
  else
    pick := 100
end;

begin
  total := 0;

  if false then
    total := 1000
  else
    total := total + 2;

  while false do
    total := total + 500;

  if not not true then
    total := total + 0;

  total := total + (3 * (4 + 1));
  total := total - 0;
  total := total + pick();

  writeln('OPT ', total)
end.
