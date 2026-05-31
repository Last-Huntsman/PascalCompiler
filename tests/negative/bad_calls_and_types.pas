program BadCallsAndTypes;

var
  x: integer;
  s: string;

function add(a, b: integer): integer;
begin
  add := a + b
end;

begin
  x := add(1);
  s := 10;
  inc(s);
  writeln(length(x))
end.
