program ReadIo;

var
  name: string;
  age: integer;

begin
  write('name age: ');
  readln(name);
  readln(age);
  writeln('HELLO ', name, ' ', age)
end.
