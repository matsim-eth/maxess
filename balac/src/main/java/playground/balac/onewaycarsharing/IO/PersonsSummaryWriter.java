package playground.balac.onewaycarsharing.IO;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import org.matsim.core.population.PersonImpl;

public class PersonsSummaryWriter
{
  private FileWriter fw = null;
  private BufferedWriter out = null;

  public PersonsSummaryWriter(String outfile)
  {
    try
    {
      this.fw = new FileWriter(outfile);
      System.out.println(outfile);
      this.out = new BufferedWriter(this.fw);
      this.out.write("Pers_Id\tAge\tGender\tLicense\tCarAv\tC\n");
      this.out.flush();
    } catch (IOException e) {
      e.printStackTrace();
     
    }
    System.out.println("    done.");
  }

  public final void close()
  {
    try
    {
      this.out.flush();
      this.out.close();
      this.fw.close();
    } catch (IOException e) {
      e.printStackTrace();
     
    }
  }

  public void write(PersonImpl person)
  {
    try
    {
      this.out.write(person.getId() + "\t");
      this.out.write(person.getAge() + "\t");
      this.out.write(person.getSex() + "\t");
      this.out.write(person.getLicense() + "\t");
      this.out.write(person.getCarAvail() + "\n");
      this.out.flush();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}