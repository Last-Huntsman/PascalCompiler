program StringsFunctions;

var
  s, part: string;
  i: integer;

function reverse(s: string): string;
var
  r: string;
begin
  r := '';
  for i := length(s) downto 1 do
    r := r + s[i];
  reverse := r
end;

begin
  s := 'abcde';
  part := copy(s, 2, 3);
  writeln('LEN ', length(s));
  writeln('POS ', pos('cd', s));
  writeln('COPY ', part);
  writeln('REV ', reverse(s))
end.
